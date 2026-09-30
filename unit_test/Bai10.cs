using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai10
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai10.csv",
            "Bai10#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai10.csv")]
        [TestMethod]
        public void TestLargest()
        {
            MethodLibrary.MethodLibrary o = new MethodLibrary.MethodLibrary();
            int ketqua_mongdoi = Convert.ToInt32(TestContext.DataRow[1]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[2]);
            int ketqua_thucte;

            try
            {
                string input = Convert.ToString(TestContext.DataRow[0]);
                int[] numbers;

                if (input == "EMPTY")
                {
                    numbers = new int[0];
                }
                else
                {
                    string[] values = input.Split(';');
                    numbers = new int[values.Length];

                    for (int i = 0; i < values.Length; i++)
                    {
                        numbers[i] = Convert.ToInt32(values[i]);
                    }
                }

                ketqua_thucte = o.Largest(numbers);
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
