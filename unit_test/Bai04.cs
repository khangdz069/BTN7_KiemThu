using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai04
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai04.csv",
            "Bai04#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai04.csv", "data_csv")]
        [TestMethod]
        public void TestTriangle()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string ketqua_mongdoi = Convert.ToString(TestContext.DataRow[3]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[4]);

            int a = Convert.ToInt32(TestContext.DataRow[0]);
            int b = Convert.ToInt32(TestContext.DataRow[1]);
            int c = Convert.ToInt32(TestContext.DataRow[2]);

            string ketqua_thucte = m.Triangle(a, b, c);

            Assert.IsFalse(mongdoi_exception);

            if (string.IsNullOrEmpty(ketqua_thucte))
            {
                ketqua_thucte = "EMPTY";
            }

            Assert.AreEqual(ketqua_mongdoi, ketqua_thucte);
        }
    }
}