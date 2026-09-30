using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai11
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai11.csv",
            "Bai11#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai11.csv")]
        [TestMethod]
        public void TestSymmetry()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();
            bool ketqua_mongdoi = Convert.ToBoolean(TestContext.DataRow[2]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[3]);
            bool ketqua_thucte;

            try
            {
                string input = Convert.ToString(TestContext.DataRow[0]);
                int n = Convert.ToInt32(TestContext.DataRow[1]);
                string[] values = input.Split(';');
                int[] numbers = new int[values.Length];

                for (int i = 0; i < values.Length; i++)
                {
                    numbers[i] = Convert.ToInt32(values[i]);
                }

                ketqua_thucte = m.IsSymmetry(numbers, n);
            }
            catch (Exception)
            {
                Assert.IsTrue(mongdoi_exception);
                return;
            }

            Assert.IsFalse(mongdoi_exception);
            Assert.AreEqual(ketqua_mongdoi, ketqua_thucte);
        }
    }
}
